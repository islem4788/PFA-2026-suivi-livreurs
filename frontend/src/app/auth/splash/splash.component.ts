import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-splash',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './splash.component.html',
  styleUrls: ['./splash.component.scss']
})
export class SplashComponent implements OnInit {

  visible = true;

  constructor(private router: Router) {}

  ngOnInit(): void {
    setTimeout(() => {
      this.visible = false;
      setTimeout(() => {
        this.router.navigate(['/welcome']);
      }, 400); // attendre la fin du fade-out
    }, 2200); // durée d'affichage du splash
  }
}
