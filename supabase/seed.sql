-- Seed: curriculum skeleton only. Everything is DRAFT and unverified (see docs/sources-and-rights.md).
-- No textbook content is stored here.

insert into public.tenants (id, name, kind)
values ('00000000-0000-0000-0000-000000000001', 'Parhlou platform', 'platform')
on conflict do nothing;

insert into public.boards (id, code, name, status)
values ('10000000-0000-0000-0000-000000000001', 'PB', 'Punjab Boards (BISE)', 'DRAFT')
on conflict do nothing;

insert into public.examination_targets (board_id, grade, session_label, status) values
  ('10000000-0000-0000-0000-000000000001', 9,  'Annual 2027 (unverified)', 'DRAFT'),
  ('10000000-0000-0000-0000-000000000001', 10, 'Annual 2027 (unverified)', 'DRAFT')
on conflict do nothing;

insert into public.subjects (id, board_id, grade, code, name, status) values
  ('20000000-0000-0000-0000-000000000009', '10000000-0000-0000-0000-000000000001', 9,  'MATH', 'Mathematics', 'DRAFT'),
  ('20000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000001', 10, 'MATH', 'Mathematics', 'DRAFT')
on conflict do nothing;

insert into public.textbook_editions (subject_id, board_id, grade, title, edition_label, publisher, medium, verified, notes, status)
values ('20000000-0000-0000-0000-000000000009', '10000000-0000-0000-0000-000000000001', 9,
        'Mathematics 9', 'National Curriculum 2023 (edition year unverified)',
        'Punjab Curriculum and Textbook Board', 'English', false,
        'Unit list from third-party sources; confirm against the official book before use.', 'DRAFT');

insert into public.chapters (edition_id, number, title, status)
select e.id, u.n, u.t, 'DRAFT'
from public.textbook_editions e,
     (values (1,'Real Numbers'),(2,'Logarithms'),(3,'Sets and Functions'),
             (4,'Factorization and Algebraic Manipulation'),(5,'Linear Equations and Inequalities'),
             (6,'Trigonometry'),(7,'Coordinate Geometry'),(8,'Logic'),(9,'Similar Figures'),
             (10,'Graphs of Functions'),(11,'Loci and Construction'),(12,'Information Handling'),
             (13,'Probability')) as u(n, t)
where e.subject_id = '20000000-0000-0000-0000-000000000009';
